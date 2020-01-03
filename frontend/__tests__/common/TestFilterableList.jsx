import React from 'react';
import {shallow,mount} from 'enzyme';
import FilterableList from '../../app/common/FilterableList.jsx';
import sinon from 'sinon';
import expect from 'expect';

describe("FilterableList", ()=>{
    const timeoutIterationLimit = 10;

    beforeEach(()=>fetch.resetMocks());

    it("should load data on textbox change with a GET request", (done)=>{
        let shouldContinue = false;
        const mockData = [
            {name: "row1", value: "value1"},
            {name: "row2", value: "value2"},
            {name: "row3", value: "value3"},
        ];

        const onChangeMock = sinon.spy();
        const contentConverterMock = sinon.spy(()=>{
            shouldContinue = true;
            return mockData;
        });

        fetch.mockResponse(JSON.stringify(mockData));

        const rendered = shallow(<FilterableList onChange={onChangeMock}
                                               value=""
                                               size={5}
                                               unfilteredContentFetchUrl="http://mock-server/endpoint"
                                               fetchUrlFilterQuery="oid"
                                               unfilteredContentConverter={contentConverterMock}
        />);

        const searchbox = rendered.find("input").first();
        searchbox.simulate("change",{target:{value: "test"}});
        rendered.setProps({},()=>{
            timeoutWaiter(()=>shouldContinue).then(()=> {
                rendered.update();
                const select = rendered.find("select");
                const elements = select.children();
                expect(fetch.mock.calls.length).toBe(1);
                expect(fetch.mock.calls[0][0]).toEqual("http://mock-server/endpoint?oid=test");
                expect(elements.length).toBe(3);
                done();
            }).catch(err=>done.fail(err));
        });  //force a re-render - see https://airbnb.io/enzyme/docs/api/ShallowWrapper/update.html
    });

    function timeoutWaiter(checkFunc, ctr) {
        return new Promise((resolve,reject)=> {
            window.setTimeout(()=>{
                if(checkFunc()){
                    resolve();
                } else {
                    const incr = ctr ? ctr+1 : 1;
                    if(incr>timeoutIterationLimit){
                        reject("Reached iteration limit")
                    } else {
                        timeoutWaiter(checkFunc, incr)
                            .then(() => resolve())
                            .catch(err=>reject(err));
                    }
                }
            }, 100);
        });
    }

    it("should pass data though a provided content converter callback", (done)=>{
        const onChangeMock = sinon.spy();
        let shouldContinue = false;

        const contentConverterMock = sinon.spy(()=>{
            shouldContinue = true;
            return [];
        });

        const mockData = [
            {name: "row1", value: "value1"},
            {name: "row2", value: "value2"},
            {name: "row3", value: "value3"},
        ];

        fetch.mockResponse(JSON.stringify(mockData));

        let valueHolder = "";

        const rendered = shallow(<FilterableList onChange={onChangeMock}
                                                 value={valueHolder}
                                                 size={5}
                                                 unfilteredContentFetchUrl="http://mock-server/endpoint"
                                                 fetchUrlFilterQuery="oid"
                                                 unfilteredContentConverter={contentConverterMock}
        />);


        const searchbox = rendered.find("input").first();
        searchbox.simulate("change",{target:{value: "test"}});
        rendered.setProps({},()=>{
            timeoutWaiter(()=>shouldContinue).then(()=> {
                const select = rendered.find("select");
                const elements = select.children();
                expect(fetch.mock.calls.length).toBe(1);
                expect(contentConverterMock.calledWith(mockData)).toBe(true);
                done();
            }).catch(err=>done.fail(err));
        });  //force a re-render - see https://airbnb.io/enzyme/docs/api/ShallowWrapper/update.html
    });

    it("should call the onChange callback when something is selected", ()=>{
        const onChangeMock = sinon.spy();
        fetch.mockResponse(JSON.stringify([
            "row1",
            "row2",
            "row3"
        ]));

        const rendered = shallow(<FilterableList onChange={onChangeMock}
                                                 value="something"
                                                 size={5}
                                                 unfilteredContentFetchUrl="http://mock-server/endpoint"/>);


        const listbox = rendered.find("select").first();
        listbox.simulate("change",{target:{value:"test"}});
        expect(onChangeMock.getCall(0).calledWith("test")).toBe(true);
    });

});